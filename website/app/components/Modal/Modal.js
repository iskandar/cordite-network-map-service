import React from 'react'
import { LoginContainer } from 'containers/Login/Login';

const loggingOut = (e, toggleModal) => {
  sessionStorage.clear();
  toggleModal();
}

export const LoginModal = (props) => {
  return(
    <div 
      className={`modal-component ${ props.style ? 'on' : '' }`} 
      data-link='default' 
      onClick={e => props.toggleModal(e)} >
      <LoginContainer 
        nmsLogin={props.nmsLogin} 
        toggleModal={props.toggleModal}/>
    </div>
  );
}

export const LogoutModal = (props) => {
  return(
    <div 
      className={`modal-component ${ props.style ? 'on' : '' }`} 
      data-link='default' 
      onClick={e => props.toggleModal(e)} >
      <div className="lm-container">
        <div className="lm-middle">
          <ModalTitle />
          <ModalContent />
          <ModalButtonGroup 
            isAuthorised={props.isAuthorised}
            toggleModal={props.toggleModal}
            />
        </div>
      </div>
    </div>
  );
}

const ModalTitle = (props) => {
  return(
   <div className="lm-title">
      <span className="fa fa-sign-out"></span>
      <strong>{` LOG OUT?`}</strong>
    </div>
  );
}

const ModalContent = (props) => {
  return(
    <div className="lm-content">
      <p>Are you sure you want to log out?</p>                    
      <p>Press No if you want to continue work. Press Yes to logout current user.</p>
    </div>    
  );
}

const ModalButtonGroup = (props) => {
  return(
    <div className="lm-footer">
      <button 
        className="btn pull-right"
        data-btn
        onClick={ e => loggingOut(e, props.toggleModal) }>
        Yes
      </button>
      <button 
        className="btn pull-right"        
        data-btn="cancel" 
        onClick={ e => props.toggleModal()}>
        No
      </button>
    </div>
  );
}